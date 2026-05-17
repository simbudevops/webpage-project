cd /home/ubuntu/simbu-project/webpage-project

# Create the version tracking file
echo -e "master=latest\ndev=v1" > branch-versions.txt

# Commit to master first
git checkout master
git add branch-versions.txt
git commit -m "Add branch version mapping"
git push origin master

# Merge to dev
git checkout dev
git merge master
git push origin dev
