# Use official Node.js image
FROM node:20-alpine

# Set working directory
WORKDIR /app

# Copy package.json and package-lock.json (if exists)
COPY app/package*.json ./

# Install dependencies
RUN npm install

# Copy application source code
COPY app/ .

# Expose ports
EXPOSE 8080 8000

# Run the application
CMD ["node", "index.js"]
