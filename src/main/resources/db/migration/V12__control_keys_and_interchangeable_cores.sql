-- control keys and interchangeable cores

CREATE TABLE control_keys (
    code CHARACTER VARYING(32) NOT NULL,
    section CHARACTER VARYING(32) NOT NULL,
    format CHARACTER VARYING(32) NOT NULL,
    bitting CHARACTER VARYING(48) NOT NULL,
    issued_on DATE NOT NULL,
    holder CHARACTER VARYING(128) NOT NULL,
    withdrawn_on DATE,
    note CHARACTER VARYING(512) NOT NULL,
    CONSTRAINT pk_control_keys PRIMARY KEY (code),
    CONSTRAINT ck_control_keys_a_control_key_has_a_section CHECK (section <> ''),
    CONSTRAINT ck_control_keys_withdrawn_after_issued CHECK (withdrawn_on is null or withdrawn_on >= issued_on)
);

CREATE INDEX ix_control_keys_holder ON control_keys (holder);

CREATE TABLE interchangeable_cores (
    serial CHARACTER VARYING(32) NOT NULL,
    format CHARACTER VARYING(32) NOT NULL,
    control_code CHARACTER VARYING(32) NOT NULL,
    housing CHARACTER VARYING(32) NOT NULL,
    installed_at CHARACTER VARYING(32),
    note CHARACTER VARYING(512) NOT NULL,
    CONSTRAINT pk_interchangeable_cores PRIMARY KEY (serial),
    CONSTRAINT uq_interchangeable_cores_installed_at_housing UNIQUE (installed_at, housing),
    CONSTRAINT fk_interchangeable_cores_control_code_control_keys FOREIGN KEY (control_code) REFERENCES control_keys (code) ON DELETE RESTRICT,
    CONSTRAINT fk_interchangeable_cores_installed_at_openings FOREIGN KEY (installed_at) REFERENCES openings (number) ON DELETE RESTRICT
);

CREATE INDEX ix_interchangeable_cores_control ON interchangeable_cores (control_code);
