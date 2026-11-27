# ==============================================================================
# Velan Production — Frontend Dockerfile (Local Development)
# Environment: Node.js 22 Alpine | Framework: React 19 + Vite 6 + Tailwind CSS
# ==============================================================================

FROM node:22-alpine

ARG VITE_API_BASE_URL
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}

WORKDIR /app

# Copy dependency manifests
COPY package.json package-lock.json ./

# Clean reproducible install of dependencies
RUN npm ci

# Copy remaining source code and configuration
COPY . .

USER node

# Expose Vite development server port
EXPOSE 5173

# Start Vite dev server listening on 0.0.0.0 for host browser accessibility
CMD ["npm", "run", "dev", "--", "--host", "0.0.0.0", "--port", "5173"]
