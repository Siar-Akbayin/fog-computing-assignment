output "fog_server_external_ip" {
  description = "The external IP address of the fog-server instance"
  value       = google_compute_instance.fog-server.network_interface[0].access_config[0].nat_ip
}