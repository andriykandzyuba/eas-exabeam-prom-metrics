{{/*
Get the namespace
*/}}
{{- define "python-web-service.namespace" -}}
{{- if .Values.namespace.name }}
{{- printf "%s" .Values.namespace.name }}
{{- else }}
{{- .Release.Namespace }}
{{- end }}
{{- end }}

{{/*
Expand the name of the chart.
*/}}
{{- define "python-web-service.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
*/}}
{{- define "python-web-service.fullname" -}}
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
{{- define "python-web-service.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "python-web-service.labels" -}}
helm.sh/chart: {{ include "python-web-service.chart" . }}
{{ include "python-web-service.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "python-web-service.selectorLabels" -}}
app.kubernetes.io/name: {{ include "python-web-service.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app: {{ include "python-web-service.name" . }}
{{- end }}

{{/*
Create the name of the service account to use
*/}}
{{- define "python-web-service.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "python-web-service.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}

{{/*
Generate the .dockerconfigjson content
*/}}
{{- define "python-web-service.dockerconfigjson" -}}
{{- $registry := .Values.containerRegistry.registry -}}
{{- $username := .Values.containerRegistry.username -}}
{{- $password := .Values.containerRegistry.password -}}
{{- $email := .Values.containerRegistry.email -}}
{{- $auth := printf "%s:%s" $username $password | b64enc -}}
{{- printf "{\"auths\":{\"%s\":{\"username\":\"%s\",\"password\":\"%s\",\"email\":\"%s\",\"auth\":\"%s\"}}}" $registry $username $password $email $auth | b64enc }}
{{- end }}
