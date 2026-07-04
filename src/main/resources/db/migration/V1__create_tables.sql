CREATE TABLE user (
    id bigint NOT NULL AUTO_INCREMENT,
    email VARCHAR(256) UNIQUE NOT NULL,
    password VARCHAR(128) NOT NULL,
    name VARCHAR(32) NOT NULL,
    role_type enum('ADMIN', 'USER') NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE book (
    id bigint NOT NULL AUTO_INCREMENT,
    title VARCHAR(128) NOT NULL,
    author VARCHAR(32) NOT NULL,
    release_date DATE NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE rental (
    id bigint NOT NULL AUTO_INCREMENT,
    user_id bigint NOT NULL,
    book_id bigint NOT NULL,
    rental_datetime DATETIME NOT NULL,
    return_deadline DATETIME NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (user_id) REFERENCES user(id),
    FOREIGN KEY (book_id) REFERENCES book(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
