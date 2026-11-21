-- filed annual returns

CREATE TABLE annual_returns (
    year INTEGER NOT NULL,
    measured_on DATE NOT NULL,
    keys_out INTEGER NOT NULL,
    masters_out INTEGER NOT NULL,
    unaccounted INTEGER NOT NULL,
    keyholders INTEGER NOT NULL,
    filed_by CHARACTER VARYING(128) NOT NULL,
    note CHARACTER VARYING(512) NOT NULL,
    CONSTRAINT pk_annual_returns PRIMARY KEY (year),
    CONSTRAINT uq_annual_returns_year UNIQUE (year),
    CONSTRAINT ck_annual_returns_keyholders_not_negative CHECK (keyholders >= 0),
    CONSTRAINT ck_annual_returns_keys_out_not_negative CHECK (keys_out >= 0),
    CONSTRAINT ck_annual_returns_masters_are_keys_too CHECK (masters_out <= keys_out),
    CONSTRAINT ck_annual_returns_masters_out_not_negative CHECK (masters_out >= 0),
    CONSTRAINT ck_annual_returns_unaccounted_are_keys_too CHECK (unaccounted <= keys_out),
    CONSTRAINT ck_annual_returns_unaccounted_not_negative CHECK (unaccounted >= 0),
    CONSTRAINT ck_annual_returns_a_real_year CHECK (year >= 1900)
);
