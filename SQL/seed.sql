-- Minimal first-install data. No production accounts, contacts, schools or business records.
BEGIN;

INSERT INTO oj_platform (oj_name, oj_url) VALUES
    ('CodeForces', 'https://codeforces.com/'),
    ('AtCoder', 'https://atcoder.jp/'),
    ('NowCoder', 'https://ac.nowcoder.com/'),
    ('Luogu', 'https://www.luogu.com.cn/');

insert into certificate_level(name, level_type, rank_type, base_rating)
values
    ('国家级特等奖','国家级', '特等奖',1000),
    ('国家级一等奖','国家级', '一等奖（金奖）',850),
    ('国家级二等奖','国家级', '二等奖（银奖）',700),
    ('国家级三等奖','国家级', '三等奖（铜奖）',550),
    ('国家级优秀奖','国家级', '优秀奖',400),
    ('国家级其他奖','国家级', '其他奖项（参与奖等）',250),
    ('省级特等奖','省级', '特等奖',600),
    ('省级一等奖','省级', '一等奖（金奖）',500),
    ('省级二等奖','省级', '二等奖（银奖）',400),
    ('省级三等奖','省级', '三等奖（铜奖）',300),
    ('省级优秀奖','省级', '优秀奖',200),
    ('省级其他奖','省级', '其他奖项（参与奖等）',100),
    ('校（市）级特等奖','校（市）级', '特等奖',300),
    ('校（市）级一等奖','校（市）级', '一等奖（金奖）',250),
    ('校（市）级二等奖','校（市）级', '二等奖（银奖）',200),
    ('校（市）级三等奖','校（市）级', '三等奖（铜奖）',150),
    ('校（市）级优秀奖','校（市）级', '优秀奖',100),
    ('校（市）级其他奖','校（市）级', '其他奖项（参与奖等）',50),
    ('院系级特等奖','院系级', '特等奖',150),
    ('院系级一等奖','院系级', '一等奖（金奖）',120),
    ('院系级二等奖','院系级', '二等奖（银奖）',90),
    ('院系级三等奖','院系级', '三等奖（铜奖）',60),
    ('院系级优秀奖','院系级', '优秀奖',30),
    ('院系级其他奖','院系级', '其他奖项（参与奖等）',10);

-- Neutral organization placeholders required by the administrator's profile.
INSERT INTO university (university_name) VALUES ('默认学校');
INSERT INTO school (university_id, school_name) VALUES (1, '默认学院');
INSERT INTO major (school_id, major_name) VALUES (1, '默认专业');

INSERT INTO sys_intro (category, content) VALUES
    ('rules', ''), ('committee', ''), ('association', ''), ('honors', ''), ('showcase_bg', '[]');

-- Login: admin / admin123456. Change the password after the first sign-in.
-- The browser sends the lowercase MD5 of the password; the server hashes that value again.
INSERT INTO users (user_name, password, email, phone, introduce, real_name, number_id,
                   gender, role, account_status, major_id, grade_year, class_name,
                   is_enrolled_stu, user_account, edu_bg)
VALUES ('管理员', 'D7C6C07A0A04BA4E65921E2F90726384', '', '', '', '管理员', '',
        1, 'super_admin', 'normal', 1, '', '', 0, 'admin', 'undergraduate');

COMMIT;
