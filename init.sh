user=$(whoami)
IPFS_PATH=/home/$user/.ipfs1 ipfs init
cp -R swarm.key /home/$user/.ipfs1
IPFS_PATH=/home/$user/.ipfs1 ipfs bootstrap rm --all 
IPFS_PATH=/home/$user/.ipfs1 ipfs bootstrap add /ip4/183.82.0.114/tcp/14001/ipfs/QmYShWaxBEYhjJ95cRZ5f6JofLpNLpn7PQPnJimwhzGPhA 
IPFS_PATH=/home/$user/.ipfs1 ipfs bootstrap add /ip4/183.82.0.114/tcp/4001/ipfs/QmeRAkURreUeWsZ5yovKSpNEC4U7UcAd91cYpbNhx4ovYW
IPFS_PATH=/home/$user/.ipfs1 ipfs config --json Experimental.Libp2pStreamMounting true
IPFS_PATH=/home/$user/.ipfs1 ipfs config --json Swarm.EnableAutoRelay true
IPFS_PATH=/home/$user/.ipfs1 ipfs config Addresses.API /ip4/127.0.0.1/tcp/5001
IPFS_PATH=/home/$user/.ipfs1 ipfs config Addresses.Gateway /ip4/127.0.0.1/tcp/8081
IPFS_PATH=/home/$user/.ipfs1 ipfs config --json Addresses.Swarm '["/ip4/0.0.0.0/tcp/4001", "/ip6/::/tcp/4001"]'

IPFS_PATH=/home/$user/.ipfs2 ipfs init
cp -R swarm.key /home/$user/.ipfs2
IPFS_PATH=/home/$user/.ipfs2 ipfs bootstrap rm --all 
IPFS_PATH=/home/$user/.ipfs2 ipfs bootstrap add /ip4/183.82.0.114/tcp/14001/ipfs/QmYShWaxBEYhjJ95cRZ5f6JofLpNLpn7PQPnJimwhzGPhA 
IPFS_PATH=/home/$user/.ipfs2 ipfs bootstrap add /ip4/183.82.0.114/tcp/4001/ipfs/QmeRAkURreUeWsZ5yovKSpNEC4U7UcAd91cYpbNhx4ovYW
IPFS_PATH=/home/$user/.ipfs2 ipfs config --json Experimental.Libp2pStreamMounting true
IPFS_PATH=/home/$user/.ipfs2 ipfs config --json Swarm.EnableAutoRelay true
IPFS_PATH=/home/$user/.ipfs2 ipfs config Addresses.API /ip4/127.0.0.1/tcp/5002
IPFS_PATH=/home/$user/.ipfs2 ipfs config Addresses.Gateway /ip4/127.0.0.1/tcp/8082
IPFS_PATH=/home/$user/.ipfs2 ipfs config --json Addresses.Swarm '["/ip4/0.0.0.0/tcp/4002", "/ip6/::/tcp/4002"]'


IPFS_PATH=/home/$user/.ipfs3 ipfs init
cp -R swarm.key /home/$user/.ipfs3
IPFS_PATH=/home/$user/.ipfs3 ipfs bootstrap rm --all 
IPFS_PATH=/home/$user/.ipfs3 ipfs bootstrap add /ip4/183.82.0.114/tcp/14001/ipfs/QmYShWaxBEYhjJ95cRZ5f6JofLpNLpn7PQPnJimwhzGPhA 
IPFS_PATH=/home/$user/.ipfs3 ipfs bootstrap add /ip4/183.82.0.114/tcp/4001/ipfs/QmeRAkURreUeWsZ5yovKSpNEC4U7UcAd91cYpbNhx4ovYW
IPFS_PATH=/home/$user/.ipfs3 ipfs config --json Experimental.Libp2pStreamMounting true
IPFS_PATH=/home/$user/.ipfs3 ipfs config --json Swarm.EnableAutoRelay true
IPFS_PATH=/home/$user/.ipfs3 ipfs config Addresses.API /ip4/127.0.0.1/tcp/5003
IPFS_PATH=/home/$user/.ipfs3 ipfs config Addresses.Gateway /ip4/127.0.0.1/tcp/8083
IPFS_PATH=/home/$user/.ipfs3 ipfs config --json Addresses.Swarm '["/ip4/0.0.0.0/tcp/4003", "/ip6/::/tcp/4003"]'


