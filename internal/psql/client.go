package psql

import (
	"context"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
)

func NewClient(ctx context.Context, dsn string) (*Client, error) {
	db, err := pgxpool.New(ctx, dsn)
	if err != nil {
		return nil, err
	}
	return &Client{db: db}, nil
}

type Client struct {
	db *pgxpool.Pool
}

func (c *Client) Close() {
	c.db.Close()
}

func (c *Client) CreateSession(ctx context.Context) (int, error) {
	var id int
	_, err := c.db.Exec(ctx, "INSERT INTO sessions (created_at) VALUES (NOW()) RETURNING id")
	if err != nil {
		return 0, err
	}
	return id, nil
}

func (c *Client) AddMessageToSession(ctx context.Context, sessionID int, role string, message string) error {
	var id int
	err := c.db.QueryRow(ctx, "INSERT INTO session_messages (session_id, role, message, created_at) VALUES ($1, $2, $3, NOW()) RETURNING id", sessionID, role, message).Scan(&id)
	if err != nil {
		return err
	}
	return nil
}

func (c *Client) GetSessionMessages(ctx context.Context, sessionID int) ([]SessionMessage, error) {
	rows, err := c.db.Query(ctx, "SELECT id, session_id, role, message, created_at FROM session_messages WHERE session_id = $1", sessionID)
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
