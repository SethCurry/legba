package events

import (
	ollama "github.com/ollama/ollama/api"
)

type AIResponseMsg struct {
	ollama.Message
}

type UserSubmitMsg struct {
	Message string
}
