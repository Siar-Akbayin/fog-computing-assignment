terraform {
  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "4.56.0"
    }
    github = {
      source  = "integrations/github"
      version = ">=5.18.0"
    }
  }
}

provider "google" {
  project     = var.gcp_project_id
  region      = var.region
  zone        = var.zone
}

provider "google-beta" {
  project     = var.gcp_project_id
  region      = var.region
  zone        = var.zone
}

### NETWORK
resource "google_compute_network" "vpc_network" {
  name = "my-network"
  auto_create_subnetworks = true
}

### FIREWALL
resource "google_compute_firewall" "allow-egress" {
  name    = "allow-egress"
  network = google_compute_network.vpc_network.name

  allow {
    protocol = "all"
  }

  direction = "EGRESS"
  priority  = 1000
}

resource "google_compute_firewall" "allow-internal" {
  name    = "allow-internal"
  network = google_compute_network.vpc_network.name

  allow {
    protocol = "icmp"
  }

  allow {
    protocol = "tcp"
    ports    = ["8089"]
  }

  allow {
    protocol = "udp"
    ports    = ["0-65535"]
  }

  source_ranges = ["0.0.0.0/0"]
}

resource "google_compute_firewall" "allow-ssh" {
  name    = "allow-ssh"
  network = google_compute_network.vpc_network.name

  allow {
    protocol = "tcp"
    ports    = ["22"]
  }

  source_ranges = ["0.0.0.0/0"]
}

  resource "google_compute_instance" "fog-server" {
  name         = "fog-server"
  machine_type = "e2-standard-2"
  zone         = var.zone

  network_interface {
    network = google_compute_network.vpc_network.name
    access_config {
      # Include this to assign an external IP address
    }
  }

  boot_disk {
    initialize_params {
      size  = 40
      image = "ubuntu-2004-focal-v20231101"
    }
  }

  metadata_startup_script = file("script.sh")
}
