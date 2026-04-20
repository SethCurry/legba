package ui

import (
	"strings"

	ollama "github.com/ollama/ollama/api"
)

type AIResponseMsg struct {
	ollama.Message
}

type ChatItem interface {
	ToLines() []string
}

type TextChatMessage struct {
	ollama.Message
}

func (m TextChatMessage) ToLines() []string {
	firstPrefix := "[" + m.Role + "] "
	numDots := len(firstPrefix)
	dots := strings.Repeat(".", numDots-1) + " "

	origLines := strings.Split(m.Content, "\n")
	numLines := len(origLines)
	lines := make([]string, numLines)
	for idx, line := range origLines {
		if idx == 0 {
			lines[idx] = firstPrefix + line
		} else {
			lines[idx] = dots + line
		}
	}
	return lines
}
