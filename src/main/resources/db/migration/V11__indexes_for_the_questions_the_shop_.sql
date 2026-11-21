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
