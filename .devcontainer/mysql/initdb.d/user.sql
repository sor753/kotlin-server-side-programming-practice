SET NAMES utf8mb4;

CREATE TABLE user (
    id INT(10) NOT NULL,
    name VARCHAR(16) NOT NULL,
    age INT(10) NOT NULL,
    profile VARCHAR(64) NOT NULL,
    PRIMARY KEY (id)
-- utf8 は最大3バイトまでしか扱えず絵文字等の4バイト文字が保存できないため、
-- 真のUTF-8を扱える utf8mb4 を指定する
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO user (id, name, age, profile) VALUES
    (1, 'Taro Yamada', 28, 'バックエンドエンジニア。Kotlin/Springが好きです。'),
    (2, 'Hanako Sato', 34, 'フロントエンド担当。UIデザインにもこだわりあり。'),
    (3, 'Jiro Suzuki', 45, 'インフラ・DevOps専門。Dockerとクラウドが得意分野。'),
    (4, 'Emily Tanaka', 22, '新卒エンジニア。日々勉強中です。'),
    (5, 'Kenji Ito', 39, 'プロジェクトマネージャー兼エンジニア。');