#!/bin/bash
# filepath: c:\Users\faund\Desktop\Memoria_components\imgpedia_backend\build_and_push.sh

# Set variables
IMAGE_NAME="elemma00/imgpedia_backend"
VERSION=$(date +"%Y%m%d%H%M")  # Use timestamp as version
LATEST="latest"

# Show what we're going to do
echo "Building and pushing Docker image: $IMAGE_NAME"
echo "Version tag: $VERSION"

# Build the image using docker-compose
echo "Building image..."
docker-compose build

# Tag with version number and latest
echo "Tagging image with version: $VERSION"
docker tag $IMAGE_NAME:$LATEST $IMAGE_NAME:$VERSION

# Login to Docker Hub
echo "Logging in to Docker Hub..."
docker login

# Push both tags to Docker Hub
echo "Pushing image with version tag: $VERSION"
docker push $IMAGE_NAME:$VERSION

echo "Pushing image with latest tag"
docker push $IMAGE_NAME:$LATEST

echo "Done! Image pushed to Docker Hub as:"
echo "  - $IMAGE_NAME:$VERSION"
echo "  - $IMAGE_NAME:$LATEST"