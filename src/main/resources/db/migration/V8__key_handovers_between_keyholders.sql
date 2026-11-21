-- key handovers between keyholders

CREATE TABLE key_transfers (
    number CHARACTER VARYING(32) NOT NULL,
    tag CHARACTER VARYING(32) NOT NULL,
    symbol CHARACTER VARYING(32) NOT NULL,
    passed_by CHARACTER VARYING(32) NOT NULL,
    passed_to CHARACTER VARYING(32) NOT NULL,
    claimed_on DATE NOT NULL,
    state CHARACTER VARYING(32) NOT NULL,
    settled_on DATE,
    note CHARACTER VARYING(512) NOT NULL,
    CONSTRAINT pk_key_transfers PRIMARY KEY (number),
    CONSTRAINT fk_key_transfers_passed_by_keyholders FOREIGN KEY (passed_by) REFERENCES keyholders (identifier) ON DELETE RESTRICT,
    CONSTRAINT fk_key_transfers_passed_to_keyholders FOREIGN KEY (passed_to) REFERENCES keyholders (identifier) ON DELETE RESTRICT,
    CONSTRAINT fk_key_transfers_tag_issued_keys FOREIGN KEY (tag) REFERENCES issued_keys (tag) ON DELETE RESTRICT,
    CONSTRAINT ck_key_transfers_a_refusal_says_why CHECK (state <> 'refused' or note <> ''),
    CONSTRAINT ck_key_transfers_a_settled_transfer_has_a_day CHECK (state = 'claimed' or settled_on is not null),
    CONSTRAINT ck_key_transfers_transfer_state_is_known CHECK (state in ('claimed', 'confirmed', 'refused', 'lapsed', 'cancelled')),
    CONSTRAINT ck_key_transfers_not_to_the_same_person CHECK (passed_by <> passed_to),
    CONSTRAINT ck_key_transfers_settled_after_claimed CHECK (settled_on is null or settled_on >= claimed_on)
);

CREATE INDEX ix_key_transfers_passed_to ON key_transfers (passed_to, state);

-- One open claim per key: the generated column holds the tag only while the claim is open, and a
-- unique index never compares two empty values, so settled claims on one key never collide.
ALTER TABLE key_transfers ADD COLUMN tag_while_claimed CHARACTER VARYING(32)
    GENERATED ALWAYS AS (CASE WHEN state = 'claimed' THEN tag END);

CREATE UNIQUE INDEX ux_key_transfers_one_open_claim ON key_transfers (tag_while_claimed);
