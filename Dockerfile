# Use an official OpenJDK 20 runtime as a parent image
FROM openjdk:20

# Set the working directory in the container
WORKDIR /usr/src/myapp

# Copy the current directory contents into the container at /usr/src/myapp
COPY src/CloudComponent.java .

# Compile the Java program
RUN javac CloudComponent.java

# Expose the port your application listens on
EXPOSE 8089

# Create the warning_cache.txt file
RUN touch /usr/src/myapp/warning_cache.txt

# Set environment variables for file locations
ENV WARNING_CACHE_FILE=/usr/src/myapp/warning_cache.txt

# Run the Server program
CMD ["java", "CloudComponent"]
