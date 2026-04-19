package ui

import "strings"

type ChatItem interface {
	ToLines() []string
}

type TextChatMessage struct {
	Role string
	Text string
}

func (m TextChatMessage) ToLines() []string {
	firstPrefix := "[" + m.Role + "] "
	numDots := len(firstPrefix)
	dots := strings.Repeat(".", 10-numDots)

	origLines := strings.Split(m.Text, "\n")
	numLines := len(origLines)
	lines := make([]string, numLines)
	for idx, line := range strings.Split(m.Text, "\n") {
		if idx == 0 {
			lines[idx] = firstPrefix + line
		} else {
			lines[idx] = dots + line
		}
	}
	return lines
}
