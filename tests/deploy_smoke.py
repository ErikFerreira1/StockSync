"""End-to-end check of the demo deployment. Writes fictitious data; use an isolated DB.

Run after starting compose.deploy.yml: python3 tests/deploy_smoke.py http://localhost:18080
Only the Python standard library is required.
"""

import argparse
import json
import re
import time
import uuid
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


def check(base_url):
    token = None

    def request(path, method="GET", payload=None, authenticated=True, expected=200):
        headers = {}
        if authenticated and token:
            headers["Authorization"] = f"Bearer {token}"
        if payload is not None:
            headers["Content-Type"] = "application/json"
        body = json.dumps(payload).encode() if payload is not None else None
        try:
            response = urlopen(Request(base_url + path, body, headers, method=method), timeout=10)
        except HTTPError as error:
            response = error
        with response:
            content = response.read()
            assert response.status == expected, f"{method} {path}: HTTP {response.status}, expected {expected}"
            return response.headers, content, response.url

    def api(path, **kwargs):
        headers, body, _ = request("/api" + path, **kwargs)
        if not body:
            return None
        assert "application/json" in headers.get("Content-Type", ""), f"JSON expected: {path}"
        return json.loads(body)

    deadline = time.monotonic() + 180
    while True:
        try:
            _, document, _ = request("/api/v3/api-docs", authenticated=False)
            assert json.loads(document)["openapi"].startswith("3.")
            break
        except (URLError, AssertionError, ValueError, ConnectionError, TimeoutError):
            if time.monotonic() >= deadline:
                raise RuntimeError("Deployment did not become ready within 180 seconds") from None
            time.sleep(2)

    for path in ("/", "/orders/1", "/listings", "/stock/1"):
        headers, html, _ = request(path)
        assert "text/html" in headers.get("Content-Type", "")
        assert b'<div id="root">' in html, f"SPA fallback failed: {path}"
    assets = re.findall(rb'src="(/assets/[^\"]+\.js)"', html)
    assert assets, "Built JavaScript asset missing from index.html"
    headers, _, _ = request(assets[0].decode())
    assert "javascript" in headers.get("Content-Type", ""), "JavaScript asset was not served"

    _, _, swagger_url = request("/swagger-ui.html", authenticated=False)
    assert "/api/swagger-ui/" in swagger_url, "Swagger redirect lost the proxy prefix"
    config = api("/v3/api-docs/swagger-config", authenticated=False)
    assert config["url"] == "/api/v3/api-docs", "Swagger API URL lost the proxy prefix"
    document = api("/v3/api-docs", authenticated=False)
    assert document["servers"][0]["url"].rstrip("/") == base_url + "/api"
    request("/api/products", authenticated=False, expected=401)

    token = api("/auth/login", method="POST", authenticated=False,
                payload={"username": "demo", "password": "stocksync-demo"})["accessToken"]
    request("/api/users", expected=403)
    request("/api/this-route-does-not-exist", expected=404)

    channels = api("/sales-channels?size=100")["content"]
    channel = next(channel for channel in channels if channel["active"])
    listings = api(f'/demo/orders/listings?salesChannelId={channel["id"]}')
    listing = next(item for item in listings
                   if item["status"] == "ACTIVE" and item["availableQuantity"] > 0)
    product_path = f'/products/{listing["productId"]}'
    before = api(product_path)["availableQuantity"]
    external = api("/demo/orders", method="POST", expected=201, payload={
        "salesChannelId": channel["id"], "listingId": listing["listingId"], "quantity": 1,
    })
    assert api(product_path)["availableQuantity"] == before, "Simulation deducted local stock early"
    sync_path = f'/sales-channels/{channel["id"]}/orders/synchronize'
    api(sync_path, method="POST", expected=204)
    assert api(product_path)["availableQuantity"] == before - 1, "Import did not deduct stock"
    api(sync_path, method="POST", expected=204)
    assert api(product_path)["availableQuantity"] == before - 1, "Repeated import deducted twice"
    orders = api("/orders?size=100&sort=id,desc")["content"]
    imported = [order for order in orders if order["externalOrderId"] == external["externalOrderId"]]
    assert len(imported) == 1 and imported[0]["status"] == "PAID" and imported[0]["stockDeducted"]
    marketplace = api(f'/demo/marketplace-listings/{listing["listingId"]}')
    assert marketplace["availableQuantity"] == before - 1, "Marketplace was not synchronized"

    # A non-seeded product can also be checked after a container recreation.
    sku = "DEPLOY-CHECK-" + uuid.uuid4().hex[:12]
    api("/products", method="POST", expected=201, payload={
        "sku": sku, "name": "Produto ficticio do teste de deploy", "description": "Smoke test",
        "basePrice": 10, "initialQuantity": 3, "minQuantity": 1,
    })
    print("OK: SPA, assets, Swagger, authentication, permissions, sale import and idempotency")
    print(f"Persistence marker: {sku}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("base_url")
    args = parser.parse_args()
    check(args.base_url.rstrip("/"))
