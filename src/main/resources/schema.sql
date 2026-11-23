CREATE TABLE IF NOT EXISTS deposits (
    id UUID PRIMARY KEY,
    customer_name VARCHAR(120) NOT NULL,
    amount NUMERIC(18, 2) NOT NULL CHECK (amount > 0)
);
