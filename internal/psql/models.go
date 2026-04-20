package psql

import "time"

type NodeType struct {
	ID          int
	Name        string
	Description string
}

type Node struct {
	ID         int
	NodeTypeID int
	Attributes map[string]any
}

type EdgeType struct {
	ID            int
	Name          string
	Bidirectional bool
	Description   string
}

type Edge struct {
	ID           int
	EdgeTypeID   int
	SourceNodeID int
	TargetNodeID int
	Attributes   map[string]any
}

type Document struct {
	ID          int
	Name        string
	Description string
	Content     string
	CreatedAt   time.Time
	UpdatedAt   time.Time
	NodeID      int
}

type Session struct {
	ID        int
	CreatedAt time.Time
	UpdatedAt time.Time
}

type SessionMessage struct {
	ID        int
	SessionID int
	Role      string
	Message   string
	CreatedAt time.Time
}
