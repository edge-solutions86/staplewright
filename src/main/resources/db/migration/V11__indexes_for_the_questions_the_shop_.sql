-- indexes for the questions the shop actually asks

CREATE INDEX ix_cylinders_group_id ON cylinders (group_id);

CREATE INDEX ix_cylinders_installed_at ON cylinders (installed_at);

CREATE INDEX ix_deposit_lines_keyholder ON deposit_lines (keyholder);

CREATE INDEX ix_deposit_lines_tag ON deposit_lines (tag);

CREATE INDEX ix_issued_keys_holder_state ON issued_keys (keyholder, state);

CREATE INDEX ix_issued_keys_state_due ON issued_keys (state, due_on);

CREATE INDEX ix_issued_keys_symbol_state ON issued_keys (symbol, state);

CREATE INDEX ix_key_requests_keyholder ON key_requests (keyholder);

CREATE INDEX ix_key_requests_state ON key_requests (state);

CREATE INDEX ix_keying_groups_name ON keying_groups (name);

CREATE INDEX ix_openings_building ON openings (building_code);

CREATE INDEX ix_openings_function ON openings (function);

CREATE INDEX ix_work_order_lines_opening ON work_order_lines (opening_number);

-- A foreign key built before any index covers its column gets an index of its own, and the planner
-- then reads that one instead of the index named above. Each key such an index now covers is built
-- again, so it rests on the named index and the one made for it goes with the old key.

ALTER TABLE cylinders DROP CONSTRAINT fk_cylinders_group_id_keying_groups;

ALTER TABLE cylinders ADD CONSTRAINT fk_cylinders_group_id_keying_groups FOREIGN KEY (group_id) REFERENCES keying_groups (id) ON DELETE SET NULL;

ALTER TABLE cylinders DROP CONSTRAINT fk_cylinders_installed_at_openings;

ALTER TABLE cylinders ADD CONSTRAINT fk_cylinders_installed_at_openings FOREIGN KEY (installed_at) REFERENCES openings (number) ON DELETE RESTRICT;

ALTER TABLE deposit_lines DROP CONSTRAINT fk_deposit_lines_tag_issued_keys;

ALTER TABLE deposit_lines ADD CONSTRAINT fk_deposit_lines_tag_issued_keys FOREIGN KEY (tag) REFERENCES issued_keys (tag) ON DELETE RESTRICT;

ALTER TABLE issued_keys DROP CONSTRAINT fk_issued_keys_keyholder_keyholders;

ALTER TABLE issued_keys ADD CONSTRAINT fk_issued_keys_keyholder_keyholders FOREIGN KEY (keyholder) REFERENCES keyholders (identifier) ON DELETE RESTRICT;

ALTER TABLE key_requests DROP CONSTRAINT fk_key_requests_keyholder_keyholders;

ALTER TABLE key_requests ADD CONSTRAINT fk_key_requests_keyholder_keyholders FOREIGN KEY (keyholder) REFERENCES keyholders (identifier) ON DELETE RESTRICT;

ALTER TABLE openings DROP CONSTRAINT fk_openings_building_code_buildings;

ALTER TABLE openings ADD CONSTRAINT fk_openings_building_code_buildings FOREIGN KEY (building_code) REFERENCES buildings (code) ON DELETE RESTRICT;
