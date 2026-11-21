-- the blind code table

CREATE TABLE blind_codes (
    symbol CHARACTER VARYING(32) NOT NULL,
    code CHARACTER VARYING(32) NOT NULL,
    assigned_on DATE NOT NULL,
    assigned_by CHARACTER VARYING(128) NOT NULL,
    CONSTRAINT pk_blind_codes PRIMARY KEY (symbol),
    CONSTRAINT uq_blind_codes_code UNIQUE (code),
    CONSTRAINT ck_blind_codes_a_code_is_something CHECK (code <> ''),
    CONSTRAINT ck_blind_codes_a_code_stands_for_something CHECK (symbol <> '')
);
