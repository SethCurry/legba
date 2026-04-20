package ui

import (
	"charm.land/bubbles/v2/textarea"
	tea "charm.land/bubbletea/v2"
	"charm.land/lipgloss/v2"
)

func newCmdCollector() *cmdCollector {
	return &cmdCollector{
		cmds: []tea.Cmd{},
	}
}

type cmdCollector struct {
	cmds []tea.Cmd
}

func (c *cmdCollector) Add(cmd tea.Cmd) {
	if cmd != nil {
		c.cmds = append(c.cmds, cmd)
	}
}

func (c *cmdCollector) Collect() tea.Cmd {
	if len(c.cmds) == 0 {
		return nil
	}
	return tea.Batch(c.cmds...)
}

func newState() *state {
	chatViewport := NewChatViewport()
	newState := &state{
		chatViewport: chatViewport,
		chatInput:    NewChatInput(),
	}
	return newState
}

type state struct {
	chatViewport ChatViewport
	chatInput    ChatInput
}

func (s state) Init() tea.Cmd {
	return textarea.Blink
}

func (s state) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	collector := newCmdCollector()

	var chatInputCmd, chatViewportCmd tea.Cmd
	s.chatInput, chatInputCmd = s.chatInput.Update(msg)
	s.chatViewport, chatViewportCmd = s.chatViewport.Update(msg)
	collector.Add(chatInputCmd)
	collector.Add(chatViewportCmd)

	switch msg := msg.(type) {
	case tea.WindowSizeMsg:
		s.chatViewport.SetWidth(msg.Width)
		s.chatViewport.SetHeight(msg.Height - s.chatInput.Height())

		return s, collector.Collect()

	// Is it a key press?
	case tea.KeyPressMsg:
		// Cool, what was the actual key pressed?
		switch msg.String() {
		// These keys should exit the program.
		case "ctrl+c", "esc":
			return s, tea.Quit
		}
	}

	return s, collector.Collect()
}

func (s state) View() tea.View {
	viewportView := s.chatViewport.View()
	v := tea.NewView(viewportView + "\n" + s.chatInput.View())
	c := s.chatInput.Cursor()
	if c != nil {
		c.Y += lipgloss.Height(s.chatInput.View())
	}
	v.Cursor = c
	v.AltScreen = true
	return v
}

func Run() error {
	_, err := tea.NewProgram(newState()).Run()
	return err
}
