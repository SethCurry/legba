package ui

import (
	"strings"

	"charm.land/bubbles/v2/viewport"
	tea "charm.land/bubbletea/v2"
	"charm.land/lipgloss/v2"
	ollama "github.com/ollama/ollama/api"
)

type ChatViewport struct {
	viewport.Model
	messages []ChatItem
}

func NewChatViewport() ChatViewport {
	vp := viewport.New(viewport.WithWidth(30), viewport.WithHeight(5))
	vp.SetContent("")
	vp.KeyMap.Left.SetEnabled(false)
	vp.KeyMap.Right.SetEnabled(false)
	return ChatViewport{
		Model: viewport.New(),
	}
}

func splitMessageAtWidth(message string, width int) []string {
	lines := []string{}
	for _, line := range strings.Split(message, "\n") {
		if len(line) > width {
			lines = append(lines, line[:width])
			lines = append(lines, line[width:])
		} else {
			lines = append(lines, line)
		}
	}
	return lines
}

func (c ChatViewport) Update(msg tea.Msg) (ChatViewport, tea.Cmd) {
	var cmd tea.Cmd
	c.Model, cmd = c.Model.Update(msg)
	switch msg := msg.(type) {
	case UserSubmitMsg:
		c.messages = append(c.messages, TextChatMessage{Message: ollama.Message{Role: "user", Content: msg.Message}})
		lines := []string{}
		for _, message := range c.messages {
			for _, line := range message.ToLines() {
				lines = append(lines, splitMessageAtWidth(line, c.Width())...)
			}
		}
		c.SetContent(lipgloss.NewStyle().Width(c.Width()).Render(strings.Join(lines, "\n")))
		return c, cmd
	case AIResponseMsg:
		c.messages = append(c.messages, TextChatMessage{Message: msg.Message})
		lines := []string{}
		for _, message := range c.messages {
			for _, line := range message.ToLines() {
				lines = append(lines, splitMessageAtWidth(line, c.Width())...)
			}
		}
		c.SetContent(lipgloss.NewStyle().Width(c.Width()).Render(strings.Join(lines, "\n")))
		return c, cmd
	case tea.WindowSizeMsg:
		c.SetWidth(msg.Width)
		return c, cmd
	case tea.KeyPressMsg:
		switch msg.String() {
		}
		return c, cmd

	}

	return c, cmd
}

func (c ChatViewport) View() string {
	return c.Model.View()
}
