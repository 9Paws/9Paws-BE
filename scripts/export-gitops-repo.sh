#!/bin/bash
set -euo pipefail

DEST_DIR="${1:-../9Paws-Gitops}"

echo "Exporting GitOps repository template to $DEST_DIR..."
mkdir -p "$DEST_DIR"
cp -r gitops-template/* "$DEST_DIR/"
cp gitops-template/.env.example "$DEST_DIR/"
echo "Template copied successfully."
echo "To publish, cd into $DEST_DIR and run:"
echo "  git init -b main"
echo "  git remote add origin https://github.com/9Paws/9Paws-Gitops.git"
echo "  git add ."
echo "  git commit -m 'feat: initial GitOps infrastructure blueprint'"
echo "  git push -u origin main"
