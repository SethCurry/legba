package ui

import (
	"charm.land/bubbles/v2/textarea"
	tea "charm.land/bubbletea/v2"
	"charm.land/lipgloss/v2"
)

type UserSubmitMsg struct {
	Message string
}

type ChatInput struct {
	textarea.Model
}

func NewChatInput() ChatInput {
	ta := textarea.New()
	ta.Placeholder = "Ask me anything..."
	ta.SetVirtualCursor(false)
	ta.Focus()

	ta.Prompt = ">"
	ta.SetWidth(30)
	ta.SetHeight(3)

	s := ta.Styles()
	s.Focused.CursorLine = lipgloss.NewStyle()
	ta.SetStyles(s)

	ta.ShowLineNumbers = false

	return ChatInput{
		Model: ta,
	}
}

func (c ChatInput) Update(msg tea.Msg) (ChatInput, tea.Cmd) {
	collector := newCmdCollector()
	var cmd tea.Cmd
	c.Model, cmd = c.Model.Update(msg)
	collector.Add(cmd)
	switch msg := msg.(type) {
	case tea.WindowSizeMsg:
		c.SetWidth(msg.Width)
		return c, cmd
	case tea.KeyPressMsg:
		switch msg.String() {
		case "ctrl+n":
			currentValue := c.Model.Value()
			collector.Add(func() tea.Msg {
				return UserSubmitMsg{Message: currentValue}
			})
			c.Reset()
			return c, collector.Collect()
		}
		return c, collector.Collect()

	}

	return c, collector.Collect()
}

func (c ChatInput) Init() tea.Cmd {
	return textarea.Blink
}

func (c ChatInput) View() string {
	return c.Model.View()
}
