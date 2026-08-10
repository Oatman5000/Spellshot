docker run -it --rm -p 5520:5520 \
  -v ../Assets.zip:/Assets.zip:ro \
  -e HYBOOT_COORDINATOR_TYPE=REDIS \
  -e HYBOOT_COORDINATOR_URI="redis://172.17.0.1:6379" \
  -e HYBOOT_COORDINATOR_PASSWORD="secretpassword" \
  -e HYBOOT_DATASTORE_REMOTE_TYPE=MONGODB \
  -e HYBOOT_DATASTORE_REMOTE_URI="mongodb://root:secretpassword@172.17.0.1:27017" \
  everlands/lobby:latest
