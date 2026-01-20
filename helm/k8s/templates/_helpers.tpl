{{/*
Get the namespace
*/}}
{{- define "k8s.namespace" -}}
{{- if .Values.namespace }}
{{- printf "%s" .Values.namespace }}
{{- else }}
{{- .Release.Namespace }}
{{- end }}
{{- end }}

{{/*
Expand the name of the chart.
*/}}
{{- define "k8s.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
*/}}
{{- define "k8s.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "k8s.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "k8s.labels" -}}
helm.sh/chart: {{ include "k8s.chart" . }}
app.kubernetes.io/name: {{ include "k8s.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Generate the .dockerconfigjson content
*/}}
{{- define "k8s.dockerconfigjson" -}}
{{- $registry := .Values.imageCredentials.registry -}}
{{- $username := .Values.imageCredentials.username -}}
{{- $password := .Values.imageCredentials.password -}}
{{- $email := .Values.imageCredentials.email -}}
{{- $auth := printf "%s:%s" $username $password | b64enc -}}
{{- printf "{\"auths\":{\"%s\":{\"username\":\"%s\",\"password\":\"%s\",\"email\":\"%s\",\"auth\":\"%s\"}}}" $registry $username $password $email $auth | b64enc }}
{{- end }}

