import { useState } from 'react'
import { login } from './api'
import type { User } from './types'

export default function App() {
  const [user, setUser] = useState<User | null>(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  async function handleLogin(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setLoading(true)
    setError('')
    const form = new FormData(event.currentTarget)
    try {
      const result = await login(String(form.get('username')), String(form.get('password')))
      setUser(result.user)
    } catch {
      setError('Não foi possível entrar. Confira seu usuário e senha.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <main className="login-page">
      <section className="login-card" aria-labelledby="login-title">
        <div className="brand-mark" aria-hidden="true"><span>SS</span></div>
        <p className="eyebrow">STOCKSYNC</p>
        <h1 id="login-title">Acesse sua conta</h1>
        <p className="login-copy">Entre para acompanhar seu estoque e seus anúncios.</p>
        {user ? (
          <div className="login-success" role="status">Login realizado para <strong>{user.username}</strong>. O painel será a próxima etapa.</div>
        ) : (
          <form onSubmit={handleLogin} className="login-form">
            <label htmlFor="username">Usuário</label>
            <input id="username" name="username" autoComplete="username" required placeholder="Seu usuário" />
            <label htmlFor="password">Senha</label>
            <input id="password" name="password" type="password" autoComplete="current-password" required placeholder="Sua senha" />
            {error && <p className="form-error" role="alert">{error}</p>}
            <button type="submit" disabled={loading}>{loading ? 'Entrando…' : 'Entrar'}</button>
          </form>
        )}
        <p className="login-footnote">Gestão de estoque e sincronização em um só lugar.</p>
      </section>
      <div className="login-aside" aria-hidden="true">
        <div className="aside-content">
          <div className="aside-grid"><span></span><span></span><span></span><span></span></div>
          <p>Seu estoque,<br />sempre em sincronia.</p>
          <small>Produtos, movimentações e anúncios conectados.</small>
        </div>
        <span className="aside-line aside-line-one"></span><span className="aside-line aside-line-two"></span>
      </div>
    </main>
  )
}
