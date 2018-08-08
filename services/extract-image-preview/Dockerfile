FROM node:8-slim

RUN apt-get update -qq && apt-get install -y \
    ghostscript \
    libgs-dev \
    imagemagick \
    poppler-utils
    
WORKDIR /server

COPY . /server
RUN npm install --unsafe-perm

EXPOSE 3000
CMD [ "npm", "start" ]