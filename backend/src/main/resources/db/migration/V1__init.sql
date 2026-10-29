CREATE TABLE users (id VARCHAR(36) PRIMARY KEY, token_hash CHAR(64) NOT NULL UNIQUE);
CREATE TABLE templates (id VARCHAR(40) PRIMARY KEY, title VARCHAR(100) NOT NULL, category VARCHAR(40) NOT NULL, description VARCHAR(300) NOT NULL, cover_url VARCHAR(200) NOT NULL, required_image_count INT NOT NULL);
CREATE TABLE assets (id VARCHAR(36) PRIMARY KEY, user_id VARCHAR(36) NOT NULL, mime_type VARCHAR(40) NOT NULL, size_bytes BIGINT NOT NULL, FOREIGN KEY(user_id) REFERENCES users(id));
CREATE TABLE tasks (id VARCHAR(36) PRIMARY KEY, user_id VARCHAR(36) NOT NULL, template_id VARCHAR(40) NOT NULL, status VARCHAR(20) NOT NULL, stage VARCHAR(30), failed_stage VARCHAR(30), error VARCHAR(300), result_url VARCHAR(200), fail_at VARCHAR(30), delay_ms INT NOT NULL, created_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3), updated_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3), FOREIGN KEY(user_id) REFERENCES users(id), FOREIGN KEY(template_id) REFERENCES templates(id), INDEX(user_id,created_at), INDEX(status,created_at));
CREATE TABLE task_assets (task_id VARCHAR(36) NOT NULL, asset_id VARCHAR(36) NOT NULL, position INT NOT NULL, PRIMARY KEY(task_id,position), FOREIGN KEY(task_id) REFERENCES tasks(id), FOREIGN KEY(asset_id) REFERENCES assets(id));
CREATE TABLE task_steps (task_id VARCHAR(36) NOT NULL, stage VARCHAR(30) NOT NULL, status VARCHAR(20) NOT NULL, started_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3), finished_at TIMESTAMP(3), PRIMARY KEY(task_id,stage), FOREIGN KEY(task_id) REFERENCES tasks(id));
INSERT INTO templates VALUES
('postcard','把旅行，寄给明天','旅行记忆','用两张旅行照片制作一张留住风景的明信片。','/demo/postcard.svg',2),
('coast','海岸慢时光','旅行记忆','一张照片，收藏一整个夏天。','/demo/coast.svg',1),
('portrait','光影人物志','人像灵感','把日常人像，变成值得收藏的封面。','/demo/portrait.svg',1),
('duo','我们的双人故事','人像灵感','两张照片，让不同瞬间在同一画面相遇。','/demo/duo.svg',2),
('garden','花园来信','创意拼贴','让三张生活碎片在花园里重新相遇。','/demo/garden.svg',3),
('mood','今日心情切片','创意拼贴','用三张图片，拼出今天的颜色。','/demo/mood.svg',3);
