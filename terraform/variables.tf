variable "gcp_project_id" {
  type = string
  description = "Your GCP project ID"
  default = "fog-computing-426814"
}

variable "region" {
  type = string
  description = "Your GCP region"
  default = "europe-west3"
}

variable "zone" {
  type = string
  description = "Your GCP zone"
  default = "europe-west3-a"
}

#variable "container_registry_link" {
#  type = string
#  description = "The link to the container registry"
#  default = "ghcr.io/siar-akbayin/"
#}
#
#variable "sudo_pw" {
#    type = string
#    sensitive = true
#    description = "The sudo password"
#}