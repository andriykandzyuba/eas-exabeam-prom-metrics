{{/*
Get the namespace
*/}}
{{- define "lob.namespace" -}}
{{- if .Values.namespace.name }}
{{- printf "%s" .Values.namespace.name }}
{{- else }}
{{- .Release.Namespace }}
{{- end }}
{{- end }}

{{/*
Expand the name of the chart.
*/}}
{{- define "lob.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
*/}}
{{- define "lob.fullname" -}}
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
{{- define "lob.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "lob.labels" -}}
helm.sh/chart: {{ include "lob.chart" . }}
{{ include "lob.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "lob.selectorLabels" -}}
app.kubernetes.io/name: {{ include "lob.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app: {{ include "lob.name" . }}
{{- end }}

{{/*
Create the name of the service account to use
*/}}
{{- define "lob.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "lob.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}

{{/*
Generate the .dockerconfigjson content
*/}}
{{- define "lob.dockerconfigjson" -}}
{{- $registry := .Values.containerRegistry.registry -}}
{{- $username := .Values.containerRegistry.username -}}
{{- $password := .Values.containerRegistry.password -}}
{{- $email := .Values.containerRegistry.email -}}
{{- $auth := printf "%s:%s" $username $password | b64enc -}}
{{- printf "{\"auths\":{\"%s\":{\"username\":\"%s\",\"password\":\"%s\",\"email\":\"%s\",\"auth\":\"%s\"}}}" $registry $username $password $email $auth | b64enc }}
{{- end }}
