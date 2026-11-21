-- the adjustment a stocktake found

ALTER TABLE blank_stock_lines ADD COLUMN adjustment INTEGER;

update blank_stock_lines set adjustment = 0 where movement = 'counted';

ALTER TABLE blank_stock_lines ADD CONSTRAINT ck_blank_stock_lines_only_a_count_carries_an_adjustment CHECK ((movement = 'counted') = (adjustment is not null));
