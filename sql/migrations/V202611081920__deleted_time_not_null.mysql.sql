-- 存量动态表 deleted_time → NOT NULL DEFAULT '1970-01-01 00:00:00.000'（复合唯一根治前置）
-- 背景：唯一索引 (列, deleted_time) 里 NULL 不判重，业务唯一只剩应用层查-插；
--       NOT NULL 后活跃行同值在数据库层真正判重。
-- 执行前必读（sql/README.md「迁移台账」节）：本脚本含数据订正（软删行错开重排），
-- 大表 ALTER 属表重建，需评估停机窗口；执行前务必先在隔离库演练（init 库+存量 dump）。
-- 回滚：ALTER TABLE `<表>` MODIFY deleted_time datetime(3) NULL（仅在各表数据已回滚软删语义后）

DELIMITER $$

DROP PROCEDURE IF EXISTS platform_deleted_time_not_null $$
CREATE PROCEDURE platform_deleted_time_not_null()
BEGIN
    DECLARE done INT DEFAULT 0;
    DECLARE tName VARCHAR(64);
    DECLARE dupTable VARCHAR(64);
    DECLARE dupCol VARCHAR(64);
    -- 圈定含 deleted_time 且仍可空的动态表（平台动态表前缀 dynamic_，排除子表关联表 dynamic_○_○ 亦含该列，一并处理）
    DECLARE tableCur CURSOR FOR
        SELECT DISTINCT c.TABLE_NAME
        FROM information_schema.COLUMNS c
        WHERE c.TABLE_SCHEMA = DATABASE()
          AND c.COLUMN_NAME = 'deleted_time'
          AND c.IS_NULLABLE = 'YES'
          AND c.TABLE_NAME LIKE 'dynamic\_%';
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

    -- ① 预检：活跃数据里业务唯一列已存在重复 → 无法自动裁决，报错让运维修数后重跑
    --    （UNI_ 索引第一列 = 业务列；第二列 deleted_time 由本脚本统一订正）
    SET done = 0;
    OPEN tableCur;
    tables_loop: LOOP
        FETCH tableCur INTO tName;
        IF done THEN LEAVE tables_loop; END IF;
        BEGIN
            DECLARE iDone INT DEFAULT 0;
            DECLARE iName VARCHAR(64);
            DECLARE iCol VARCHAR(64);
            DECLARE idxCur2 CURSOR FOR SELECT idx_name, idx_col FROM platform_idx_tmp;
            DECLARE CONTINUE HANDLER FOR NOT FOUND SET iDone = 1;
            DROP TEMPORARY TABLE IF EXISTS platform_idx_tmp;
            CREATE TEMPORARY TABLE platform_idx_tmp (idx_name VARCHAR(64), idx_col VARCHAR(64));
            SET @s = CONCAT('INSERT INTO platform_idx_tmp SELECT INDEX_NAME, COLUMN_NAME FROM information_schema.STATISTICS',
                    ' WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ''', tName, '''',
                    ' AND LEFT(INDEX_NAME, 4) = ''UNI_'' AND SEQ_IN_INDEX = 1');
            PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
            OPEN idxCur2;
            idx_loop: LOOP
                FETCH idxCur2 INTO iName, iCol;
                IF iDone THEN LEAVE idx_loop; END IF;
                SET @dupCheck = CONCAT(
                    'SELECT COUNT(*) INTO @dupCnt FROM (SELECT `', iCol, '` FROM `', tName,
                    '` WHERE deleted_flag = ''N'' GROUP BY `', iCol, '` HAVING COUNT(*) > 1 LIMIT 1) d');
                PREPARE st FROM @dupCheck; EXECUTE st; DEALLOCATE PREPARE st;
                IF @dupCnt > 0 THEN
                    SET dupTable = tName;
                    SET dupCol = iCol;
                    DROP TEMPORARY TABLE IF EXISTS platform_idx_tmp;
                    SIGNAL SQLSTATE '45000'
                        SET MESSAGE_TEXT = '活跃数据存在业务唯一列重复：按表 UNI_ 索引列 GROUP BY HAVING COUNT>1 找出重复组修数后重跑';
                END IF;
            END LOOP;
            CLOSE idxCur2;
            DROP TEMPORARY TABLE IF EXISTS platform_idx_tmp;
        END;
    END LOOP tables_loop;
    CLOSE tableCur;

    -- ② 软删行时间错开：每张表的软删行按 (id) 顺序重排为纪元零点+第 n 秒，
    --    保证软删行两两不共享 (业务列, deleted_time)，订正后索引可建、软删可共存
    SET done = 0;
    OPEN tableCur;
    restagger_loop: LOOP
        FETCH tableCur INTO tName;
        IF done THEN LEAVE restagger_loop; END IF;
        SET @s = CONCAT(
            'UPDATE `', tName, '` t JOIN (SELECT id, ROW_NUMBER() OVER (ORDER BY id) rn FROM `', tName,
            '` WHERE deleted_flag = ''Y'') d ON t.id = d.id',
            ' SET t.deleted_time = DATE_ADD(''1970-01-01 00:00:00.000'', INTERVAL d.rn SECOND)',
            ' WHERE t.deleted_flag = ''Y''');
        PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
        -- ③ 活跃行 NULL → 哨兵默认
        SET @s = CONCAT('UPDATE `', tName, '` SET deleted_time = ''1970-01-01 00:00:00.000'' WHERE deleted_time IS NULL');
        PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
        -- ④ MODIFY NOT NULL
        SET @s = CONCAT('ALTER TABLE `', tName, '` MODIFY deleted_time datetime(3) NOT NULL DEFAULT ''1970-01-01 00:00:00.000''');
        PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
    END LOOP restagger_loop;
    CLOSE tableCur;

    DROP TEMPORARY TABLE IF EXISTS platform_idx_tmp;
END $$

DELIMITER ;

CALL platform_deleted_time_not_null();
DROP PROCEDURE IF EXISTS platform_deleted_time_not_null;

CREATE TABLE IF NOT EXISTS platform_schema_history (
  id varchar(36) NOT NULL,
  version varchar(32) NOT NULL,
  script_name varchar(200) NOT NULL,
  dialect varchar(16) NOT NULL,
  checksum varchar(64) DEFAULT NULL,
  applied_by varchar(64) NOT NULL DEFAULT 'manual',
  applied_at datetime NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_schema_script (script_name, dialect)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据库结构变更台账';

INSERT INTO platform_schema_history
    (id, version, script_name, dialect, applied_by, applied_at)
VALUES (REPLACE(UUID(), '-', ''), 'V202611081920',
        'V202611081920__deleted_time_not_null.mysql.sql', 'mysql', 'manual', NOW());
