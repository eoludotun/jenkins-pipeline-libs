#!/bin/bash

group_tag=$1
base_build=$(awk -F '_' '{ print $1 "_" $2}'<<< $group_tag)

export PATH=/usr/local/bin:$PATH

aws="aws --region us-east-1"


# Get instance IDs of previous instances tagged with 'Build:folio_BUILDNAME_stable'...
filter="Name=resource-type,Values=instance Name=key,Values=Build Name=value,Values=${base_build}_stable"
current_stable=$($aws ec2 describe-tags --filters $filter --query Tags[*].ResourceId)

# And re-tag those instances to Build:folio_BUILDNAME_old
if [ -n "$current_stable" ]; then 
  echo "Re-tagging previous builds to '${base_build}_old'"
  for i in $current_stable
  do
    $aws ec2 create-tags --resources $i --tags Key=Build,Value=${base_build}_old
  done
else
  echo "No matching tags for Build: '${base_build}_stable' found."
fi

# Get instance ID(s) of 'stable' folio platform we just built...
filter="Name=resource-type,Values=instance Name=key,Values=Group Name=value,Values=${group_tag}"
new_stable=$($aws ec2 describe-tags --filters $filter --query Tags[*].ResourceId)


# Add 'Build:folio_BUILDNAME_stable' tag.
if [ -n "$new_stable" ]; then 
  echo "Adding tag 'Build:${base_build}_stable' to current build Group:${group_tag}."
  for i in $new_stable
  do
    $aws ec2 create-tags --resources $i --tags Key=Build,Value=${base_build}_stable
  done
else
  echo "No matching tags for Group:'${group_tag}' found."
  exit 1
fi

