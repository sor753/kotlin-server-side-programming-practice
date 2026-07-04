-- password は開発用のダミー値（平文）。本番相当のデータでは必ずハッシュ化した値を使うこと。
INSERT INTO user (id, email, password, name, role_type) VALUES
    (1, 'taro.yamada@example.com', 'password', 'Taro Yamada', 'ADMIN'),
    (2, 'hanako.sato@example.com', 'password', 'Hanako Sato', 'USER'),
    (3, 'jiro.suzuki@example.com', 'password', 'Jiro Suzuki', 'USER'),
    (4, 'emily.tanaka@example.com', 'password', 'Emily Tanaka', 'USER'),
    (5, 'kenji.ito@example.com', 'password', 'Kenji Ito', 'USER');
