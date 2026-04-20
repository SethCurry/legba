package main

import (
	"context"
	"fmt"
	"log"
	"os"

	"github.com/SethCurry/legba/internal/legba"
	"github.com/SethCurry/legba/internal/ui"
	"github.com/urfave/cli/v3"
	"go.uber.org/zap"
)

func main() {
	logger := zap.Must(zap.NewDevelopment())
	defer logger.Sync()
	zap.ReplaceGlobals(logger)

	app := &cli.Command{
		Name:    "legba",
		Usage:   "A CLI for the Legba project",
		Version: "0.0.1",
		Commands: []*cli.Command{
			{
				Name:  "list-models",
				Usage: "List all available models",
				Action: func(ctx context.Context, cmd *cli.Command) error {
					cfg, err := legba.LoadConfig()
					if err != nil {
						return err
					}

					ollamaClient, err := cfg.Providers.Ollama.Client()
					if err != nil {
						return err
					}

					models, err := ollamaClient.List(ctx)
					if err != nil {
						return err
					}

					for _, model := range models.Models {
						fmt.Println(model.Model)
					}
					return nil
				},
			},
			{
				Name:  "agent",
				Usage: "Run an agent",
				Action: func(ctx context.Context, cmd *cli.Command) error {
					cfg, err := legba.LoadConfig()
					if err != nil {
						return err
					}

					ollamaClient, err := cfg.Providers.Ollama.Client()
					if err != nil {
						return err
					}
					return ui.Run(ollamaClient)
				},
			},
		},
	}

	if err := app.Run(context.Background(), os.Args); err != nil {
		log.Fatal(err)
	}
}
