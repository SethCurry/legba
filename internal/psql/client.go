package psql

import (
	"context"
	"fmt"
	"time"

	"github.com/Masterminds/squirrel"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

func NewClient(ctx context.Context, dsn string) (*Client, error) {
	db, err := pgxpool.New(ctx, dsn)
	if err != nil {
		return nil, err
	}
	return &Client{
		db: db,
		sq: squirrel.StatementBuilder.PlaceholderFormat(squirrel.Dollar),
	}, nil
}

type Client struct {
	db *pgxpool.Pool
	sq squirrel.StatementBuilderType
}

func (c *Client) Close() {
	c.db.Close()
}

func (c *Client) WithTx(ctx context.Context, fn func(*Transaction) error) error {
	tx, err := c.db.BeginTx(ctx, pgx.TxOptions{})
	if err != nil {
		return fmt.Errorf("begin tx: %w", err)
	}
	defer tx.Rollback(ctx)
	wrapped := &Transaction{tx: tx, sq: c.sq}

	if err := fn(wrapped); err != nil {
		if err := tx.Rollback(ctx); err != nil {
			return fmt.Errorf("rollback: %w", err)
		}
		return err
	}

	return nil
}

type Transaction struct {
	tx pgx.Tx
	sq squirrel.StatementBuilderType
}

func (t *Transaction) Commit(ctx context.Context) error {
	return t.tx.Commit(ctx)
}

func (t *Transaction) Rollback(ctx context.Context) error {
	return t.tx.Rollback(ctx)
}

type SessionClient struct {
	*Transaction
}

func (c *SessionClient) CreateSession(ctx context.Context) (int, error) {
	var id int
	_, err := c.tx.Exec(ctx, "INSERT INTO sessions (created_at) VALUES (NOW()) RETURNING id")
	if err != nil {
		return 0, err
	}
	return id, nil
}

func (c *SessionClient) AddMessage(ctx context.Context, sessionID int, role string, message string) error {
	var id int
	err := c.tx.QueryRow(ctx, "INSERT INTO session_messages (session_id, role, message, created_at) VALUES ($1, $2, $3, NOW()) RETURNING id", sessionID, role, message).Scan(&id)
	if err != nil {
		return err
	}
	return nil
}

func (c *SessionClient) GetMessages(ctx context.Context, sessionID int) ([]SessionMessage, error) {
	rows, err := c.tx.Query(ctx, "SELECT id, session_id, role, message, created_at FROM session_messages WHERE session_id = $1", sessionID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	sessionMessages := []SessionMessage{}
	for rows.Next() {
		var id int
		var sessionID int
		var role string
		var message string
		var createdAt time.Time
		err := rows.Scan(&id, &sessionID, &role, &message, &createdAt)
		if err != nil {
			return nil, err
		}
		sessionMessages = append(sessionMessages, SessionMessage{ID: id, SessionID: sessionID, Role: role, Message: message, CreatedAt: createdAt})
	}
	return sessionMessages, nil
}
