package legba

import (
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"time"

	"github.com/ollama/ollama/api"
)

func newOllamaRoundTripper(apiKey string) *ollamaRoundTripper {
	return &ollamaRoundTripper{authorizationHeader: fmt.Sprintf("Bearer %s", apiKey)}
}

type ollamaRoundTripper struct {
	authorizationHeader string
}

func (r *ollamaRoundTripper) RoundTrip(req *http.Request) (*http.Response, error) {
	req.Header.Set("Authorization", r.authorizationHeader)
	return http.DefaultTransport.RoundTrip(req)
}

type OllamaConfig struct {
	BaseURL string `json:"base_url"`
	APIKey  string `json:"api_key"`
	Timeout string `json:"timeout"`
}

func (c *OllamaConfig) Client() (*api.Client, error) {
	// TODO: if ~/.ollama/id_ed25519 does not exist, we need to generate one
	if c.BaseURL == "" {
		return nil, fmt.Errorf("ollama base_url is required")
	}

	parsedURL, err := url.Parse(c.BaseURL)
	if err != nil {
		return nil, fmt.Errorf("ollama base_url fails to parse into a URL: %w", err)
	}

	var timeout time.Duration
	if c.Timeout != "" {
		timeout, err = time.ParseDuration(c.Timeout)
		if err != nil {
			return nil, fmt.Errorf("ollama timeout fails to parse into a duration: %w", err)
		}
	} else {
		timeout = 600 * time.Second
	}

	httpClient := &http.Client{
		Transport: newOllamaRoundTripper(c.APIKey),
		Timeout:   timeout,
	}
	client := api.NewClient(parsedURL, httpClient)

	return client, nil
}

type ProviderConfig struct {
	Ollama OllamaConfig `json:"ollama"`
}

// Config is the shape of ~/.config/legba/config.json. Add exported fields with
// `json` tags as configuration keys are introduced.
type Config struct {
	Providers ProviderConfig `json:"providers"`
}

// ConfigPath returns the absolute path to the default legba config file
// (~/.config/legba/config.json).
func ConfigPath() (string, error) {
	home, err := os.UserHomeDir()
	if err != nil {
		return "", fmt.Errorf("user home directory: %w", err)
	}
	return filepath.Join(home, ".config", "legba", "config.json"), nil
}

// LoadConfig reads and parses the default config file.
func LoadConfig() (*Config, error) {
	path, err := ConfigPath()
	if err != nil {
		return nil, err
	}
	return LoadConfigFile(path)
}

// LoadConfigFile reads and parses config JSON from path.
func LoadConfigFile(path string) (*Config, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, fmt.Errorf("read config %q: %w", path, err)
	}
	var cfg Config
	if err := json.Unmarshal(data, &cfg); err != nil {
		return nil, fmt.Errorf("parse config %q: %w", path, err)
	}
	return &cfg, nil
}
