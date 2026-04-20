package ui

import (
	"fmt"
	"strings"

	"charm.land/bubbles/v2/cursor"
	"charm.land/bubbles/v2/textarea"
	"charm.land/bubbles/v2/viewport"
	tea "charm.land/bubbletea/v2"
	"charm.land/lipgloss/v2"
	ollama "github.com/ollama/ollama/api"
)

func newState() state {
	vp := viewport.New(viewport.WithWidth(30), viewport.WithHeight(5))
	vp.SetContent("Welcome!")
	vp.KeyMap.Left.SetEnabled(false)
	vp.KeyMap.Right.SetEnabled(false)

	return state{
		chatViewport: vp,
		chatInput:    NewChatInput(),
		messages:     []ChatItem{},
	}
}

type state struct {
	chatViewport viewport.Model
	messages     []ChatItem
	chatInput    ChatInput
}

func (s state) Init() tea.Cmd {
	return textarea.Blink
}

func (s state) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	switch msg := msg.(type) {
	case tea.KeyboardEnhancementsMsg:
		fmt.Println("we got a keyboard enhancement msg")
		fmt.Println(msg.Flags)
		return s, nil
	case tea.WindowSizeMsg:
		s.chatViewport.SetWidth(msg.Width)
		s.chatInput.SetWidth(msg.Width)
		s.chatViewport.SetHeight(msg.Height - s.chatInput.Height())

		if len(s.messages) > 0 {
			lines := []string{}
			for _, message := range s.messages {
				lines = append(lines, message.ToLines()...)
			}
			s.chatViewport.SetContent(lipgloss.NewStyle().Width(s.chatViewport.Width()).Render(strings.Join(lines, "\n")))
		}
		s.chatViewport.GotoBottom()
		return s, nil

	// Is it a key press?
	case tea.KeyPressMsg:

		// Cool, what was the actual key pressed?
		switch msg.String() {

		// These keys should exit the program.
		case "ctrl+c", "esc":
			return s, tea.Quit

		case "ctrl+n":
			s.messages = append(s.messages, TextChatMessage{Message: ollama.Message{Role: "user", Content: s.chatInput.Value()}})
			lines := []string{}
			for _, message := range s.messages {
				lines = append(lines, message.ToLines()...)
			}
			s.chatViewport.SetContent(lipgloss.NewStyle().Width(s.chatViewport.Width()).Render(strings.Join(lines, "\n")))
			s.chatInput.Reset()
			s.chatViewport.GotoBottom()
			return s, nil

		default:
			var cmd tea.Cmd
			s.chatInput, cmd = s.chatInput.Update(msg)
			return s, cmd
		}

	case cursor.BlinkMsg:
		var cmd tea.Cmd
		s.chatInput, cmd = s.chatInput.Update(msg)
		return s, cmd
	}

	return s, nil
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
