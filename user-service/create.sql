
    create table device_tokens (
        is_active boolean,
        created_at timestamp(6) not null,
        last_used_at timestamp(6),
        updated_at timestamp(6) not null,
        device_type varchar(20) not null check (device_type in ('MOBILE','TABLET','TV','DESKTOP','UNKNOWN')),
        device_id varchar(255) not null,
        device_name varchar(255),
        id varchar(255) not null,
        token TEXT not null,
        user_id varchar(255) not null,
        primary key (id),
        constraint uk_user_device_token unique (user_id, device_id, token)
    );

    create table invalidated_tokens (
        expiry_time timestamp(6) not null,
        invalidated_at timestamp(6) not null,
        token_type varchar(20),
        id varchar(255) not null,
        reason TEXT,
        user_id varchar(255),
        primary key (id)
    );

    create table login_history (
        created_at timestamp(6) not null,
        device_type varchar(20) check (device_type in ('MOBILE','TABLET','TV','DESKTOP','UNKNOWN')),
        login_method varchar(20) not null check (login_method in ('LOCAL','OTP','QR_CODE','GOOGLE')),
        status varchar(20) not null check (status in ('SUCCESS','FAILED','BLOCKED','REQUIRES_2FA')),
        ip_address varchar(45),
        device_id varchar(255),
        failure_reason TEXT,
        id varchar(255) not null,
        location varchar(255),
        qr_code_id varchar(255),
        user_agent TEXT,
        user_id varchar(255),
        primary key (id)
    );

    create table otp_codes (
        attempts integer,
        is_used boolean,
        code varchar(6) not null,
        created_at timestamp(6) not null,
        expires_at timestamp(6) not null,
        used_at timestamp(6),
        phone varchar(20),
        type varchar(20) not null check (type in ('LOGIN','REGISTER','RESET_PASSWORD','VERIFY_PHONE','VERIFY_EMAIL','CHANGE_PHONE','LINK_ACCOUNT')),
        ip_address varchar(45),
        email varchar(255),
        id varchar(255) not null,
        primary key (id)
    );

    create table qr_codes (
        failed_attempts integer,
        confirmed_at timestamp(6),
        created_at timestamp(6) not null,
        expires_at timestamp(6) not null,
        scanned_at timestamp(6),
        updated_at timestamp(6) not null,
        device_type varchar(20) check (device_type in ('MOBILE','TABLET','TV','DESKTOP','UNKNOWN')),
        qr_type varchar(20) not null check (qr_type in ('LOGIN','PAYMENT','ADD_FRIEND')),
        scanned_device_type varchar(20) check (scanned_device_type in ('MOBILE','TABLET','TV','DESKTOP','UNKNOWN')),
        status varchar(20) not null check (status in ('PENDING','SCANNED','CONFIRMED','EXPIRED','CANCELLED')),
        ip_address varchar(45),
        scanned_ip_address varchar(45),
        device_id varchar(255),
        device_info TEXT,
        id varchar(255) not null,
        location varchar(255),
        qr_data TEXT not null unique,
        scanned_device_id varchar(255),
        scanned_device_info TEXT,
        user_id varchar(255),
        primary key (id)
    );

    create table qr_login_sessions (
        authorized_at timestamp(6),
        created_at timestamp(6) not null,
        rejected_at timestamp(6),
        updated_at timestamp(6) not null,
        status varchar(20) not null check (status in ('WAITING','AUTHORIZED','REJECTED','EXPIRED')),
        id varchar(255) not null,
        qr_code_id varchar(255) not null unique,
        rejection_reason TEXT,
        session_id varchar(255) unique,
        user_id varchar(255),
        primary key (id)
    );

    create table user_sessions (
        is_active boolean,
        created_at timestamp(6) not null,
        expires_at timestamp(6) not null,
        last_active_at timestamp(6) not null,
        refresh_expires_at timestamp(6),
        revoked_at timestamp(6),
        device_type varchar(20) check (device_type in ('MOBILE','TABLET','TV','DESKTOP','UNKNOWN')),
        login_method varchar(20) check (login_method in ('LOCAL','OTP','QR_CODE','GOOGLE')),
        ip_address varchar(45),
        refresh_token varchar(500) unique,
        session_token varchar(500) not null unique,
        device_id varchar(255),
        device_name varchar(255),
        id varchar(255) not null,
        revoked_reason TEXT,
        user_agent TEXT,
        user_id varchar(255) not null,
        primary key (id)
    );

    create table users (
        date_of_birth date,
        is_active boolean,
        is_blocked boolean,
        is_email_verified boolean,
        is_phone_verified boolean,
        blocked_until timestamp(6),
        created_at timestamp(6) not null,
        deleted_at timestamp(6),
        email_verified_at timestamp(6),
        last_login_at timestamp(6),
        phone_verified_at timestamp(6),
        updated_at timestamp(6) not null,
        gender varchar(10) check (gender in ('MALE','FEMALE','OTHER')),
        account_type varchar(20) not null check (account_type in ('USER','OA','ADMIN')),
        phone varchar(20) not null unique,
        google_id varchar(100) unique,
        avatar_url varchar(500),
        cover_url varchar(500),
        bio TEXT,
        blocked_reason TEXT,
        email varchar(255) unique,
        full_name varchar(255) not null,
        id varchar(255) not null,
        password_hash varchar(255),
        primary key (id)
    );

    create index idx_device_user 
       on device_tokens (user_id);

    create index idx_device_id 
       on device_tokens (device_id);

    create index idx_device_active 
       on device_tokens (is_active);

    create index idx_invalidated_expiry 
       on invalidated_tokens (expiry_time);

    create index idx_invalidated_user 
       on invalidated_tokens (user_id);

    create index idx_login_history_user 
       on login_history (user_id, created_at);

    create index idx_login_status 
       on login_history (status);

    create index idx_login_method 
       on login_history (login_method);

    create index idx_otp_phone 
       on otp_codes (phone, expires_at);

    create index idx_otp_email 
       on otp_codes (email, expires_at);

    create index idx_otp_type 
       on otp_codes (type, is_used);

    create index idx_qr_user 
       on qr_codes (user_id);

    create index idx_qr_status 
       on qr_codes (status, expires_at);

    create index idx_qr_data 
       on qr_codes (qr_data);

    create index idx_qr_login_user 
       on qr_login_sessions (user_id);

    create index idx_qr_login_qr 
       on qr_login_sessions (qr_code_id);

    create index idx_qr_login_status 
       on qr_login_sessions (status);

    create index idx_sessions_user 
       on user_sessions (user_id);

    create index idx_sessions_token 
       on user_sessions (session_token);

    create index idx_sessions_refresh 
       on user_sessions (refresh_token);

    create index idx_sessions_device 
       on user_sessions (device_id);

    create index idx_sessions_expires 
       on user_sessions (expires_at);

    create index idx_users_phone 
       on users (phone);

    create index idx_users_email 
       on users (email);

    create index idx_users_account_type 
       on users (account_type);

    create index idx_users_is_active 
       on users (is_active);

    alter table if exists device_tokens 
       add constraint FKhc7d11bnr8x9gs5biohdhnx1c 
       foreign key (user_id) 
       references users;

    alter table if exists invalidated_tokens 
       add constraint FKldq5i2hcq6ncecr5s22ka3kov 
       foreign key (user_id) 
       references users;

    alter table if exists login_history 
       add constraint FK20v0mimmdegh2afs39uixlxpm 
       foreign key (user_id) 
       references users;

    alter table if exists qr_codes 
       add constraint FK3fuowljt7nslh9cm23fq4pfyt 
       foreign key (user_id) 
       references users;

    alter table if exists qr_login_sessions 
       add constraint FKm0wxr1atvsc57wbwq887oc77k 
       foreign key (qr_code_id) 
       references qr_codes;

    alter table if exists qr_login_sessions 
       add constraint FKiav9hn3i9yd55ovdto0t4fplh 
       foreign key (session_id) 
       references user_sessions;

    alter table if exists qr_login_sessions 
       add constraint FKis0isa8y2vb8p5t0yqo6q8r4b 
       foreign key (user_id) 
       references users;

    alter table if exists user_sessions 
       add constraint FK8klxsgb8dcjjklmqebqp1twd5 
       foreign key (user_id) 
       references users;
