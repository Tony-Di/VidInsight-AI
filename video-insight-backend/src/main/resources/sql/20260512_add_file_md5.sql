ALTER TABLE video_info
    ADD COLUMN file_md5 varchar(32) NULL COMMENT '视频文件 MD5，用于内容去重' AFTER source_url;

CREATE INDEX idx_video_info_file_md5 ON video_info(file_md5);
