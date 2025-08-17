-- Database initialization script
-- File: database/init.sql

-- Create database (if running as superuser)
-- CREATE DATABASE usermgmt;

-- Connect to the database
-- \c usermgmt;

-- Create enum types for user status and role
CREATE TYPE user_status AS ENUM ('ACTIVE', 'INACTIVE', 'SUSPENDED');
CREATE TYPE user_role AS ENUM ('USER', 'ADMIN');

-- Users table
CREATE TABLE IF NOT EXISTS users (
    id SERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(50) NOT NULL,
    last_name VARCHAR(50) NOT NULL,
    phone VARCHAR(20),
    status user_status DEFAULT 'ACTIVE',
    role user_role DEFAULT 'USER',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_login TIMESTAMP
);

-- User sessions table
CREATE TABLE IF NOT EXISTS user_sessions (
    session_id VARCHAR(255) PRIMARY KEY,
    user_id INTEGER REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    is_active BOOLEAN DEFAULT TRUE
);

-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_users_status ON users(status);
CREATE INDEX IF NOT EXISTS idx_sessions_user_id ON user_sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_sessions_expires ON user_sessions(expires_at);

-- Insert sample data for testing
INSERT INTO users (username, email, password_hash, first_name, last_name, phone, role) 
VALUES 
    ('admin', 'admin@example.com', '$2a$10$N9qo8uLOickgx2ZMRZoMye3JYL6JIiqjLUy9OGx7pKFQSvUkv4vRi', 'Admin', 'User', '+1-555-000-0001', 'ADMIN'),
    ('john_doe', 'john@example.com', '$2a$10$N9qo8uLOickgx2ZMRZoMye3JYL6JIiqjLUy9OGx7pKFQSvUkv4vRi', 'John', 'Doe', '+1-555-123-4567', 'USER'),
    ('jane_smith', 'jane@example.com', '$2a$10$N9qo8uLOickgx2ZMRZoMye3JYL6JIiqjLUy9OGx7pKFQSvUkv4vRi', 'Jane', 'Smith', '+1-555-987-6543', 'USER'),
    ('bob_wilson', 'bob@example.com', '$2a$10$N9qo8uLOickgx2ZMRZoMye3JYL6JIiqjLUy9OGx7pKFQSvUkv4vRi', 'Bob', 'Wilson', '+1-555-555-5555', 'USER')
ON CONFLICT (username) DO NOTHING;

-- Note: The password hash above corresponds to "password123" 
-- This is for testing only - never use this in production!

-- Update function for updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
   NEW.updated_at = CURRENT_TIMESTAMP;
   RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger to automatically update updated_at
CREATE TRIGGER update_users_updated_at 
    BEFORE UPDATE ON users 
    FOR EACH ROW 
    EXECUTE FUNCTION update_updated_at_column();