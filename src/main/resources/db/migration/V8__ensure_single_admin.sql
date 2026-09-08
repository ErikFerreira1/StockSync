CREATE UNIQUE INDEX uk_app_users_single_admin
    ON app_users (role)
    WHERE role = 'ADMIN';
