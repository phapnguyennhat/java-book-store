#!/bin/sh
# Runs when LocalStack is ready: private bucket that browsers may PUT/GET via presigned URLs
set -e
awslocal s3 mb "s3://$S3_BUCKET" || true
awslocal s3api put-bucket-cors --bucket "$S3_BUCKET" --cors-configuration '{
  "CORSRules": [{"AllowedOrigins": ["*"], "AllowedMethods": ["PUT", "GET"], "AllowedHeaders": ["*"], "MaxAgeSeconds": 3000}]
}'
