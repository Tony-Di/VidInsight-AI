ALTER TABLE video_info
    ADD COLUMN audio_url varchar(500) NULL AFTER source_url,
    ADD COLUMN transcript longtext NULL AFTER audio_url;
