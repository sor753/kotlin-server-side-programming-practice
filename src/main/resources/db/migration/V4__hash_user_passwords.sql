-- V2__seed.sqlで投入したユーザーのpasswordが平文("password")のままだったため、
-- BCryptPasswordEncoderでハッシュ化した値に置き換える。
-- 元の値は全ユーザー共通で"password"だったため、ハッシュ値も共通のもので更新する。
-- ハッシュはこのプロジェクトのBCryptPasswordEncoder(strength=10)で生成し、matches()で検証済み。
UPDATE user SET password = '$2a$10$APLYCYpJHgvFHvdmUSUuYOh7o6.mMcpkKpk2MG2RCMuJE2DGBNfBG';
