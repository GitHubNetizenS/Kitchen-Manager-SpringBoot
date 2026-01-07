USE `kitchen-manager`;

CREATE TABLE ingredient_idf (
    ingredient_id   INT NOT NULL COMMENT    '原料ID，对应ingredient表主键',
    idf_value       DOUBLE NOT NULL COMMENT '该原料的IDF值',
    PRIMARY KEY (ingredient_id)
) COMMENT='原料IDF全局表（TF-IDF用）';