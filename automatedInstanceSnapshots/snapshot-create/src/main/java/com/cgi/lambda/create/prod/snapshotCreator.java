package com.cgi.lambda.create.prod;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.CreateSnapshotRequest;
import com.amazonaws.services.ec2.model.DeleteSnapshotRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.DescribeSnapshotsRequest;
import com.amazonaws.services.ec2.model.DescribeSnapshotsResult;
import com.amazonaws.services.ec2.model.EbsInstanceBlockDevice;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceBlockDeviceMapping;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.ResourceType;
import com.amazonaws.services.ec2.model.Snapshot;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.TagSpecification;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

public class snapshotCreator implements RequestHandler<Object, String> {
	final AmazonEC2 ec2 = AmazonEC2ClientBuilder.defaultClient();
	private int retentiondays=14;
	@Override
	public String handleRequest(Object input, Context context) {
		String strDate=getdeletedate();
		context.getLogger().log("Input: " + input);
		ArrayList<Instance> instances=getListOfInstances();
		ArrayList<EbsInstanceBlockDevice> volumes=listVolumes(instances);
		createBackUpsVolumes(volumes,strDate);		
		removeOldSnapShots();
		return "";
	}

	protected String getdeletedate() {
		Calendar c= Calendar.getInstance();
		c.add(Calendar.DATE, retentiondays);
		Date date = c.getTime();
		DateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy");  
		String strDate = dateFormat.format(date);
		return strDate;
	}


	/**
	 * Lists instances that have backup tag enabled
	 */

	public ArrayList<Instance> getListOfInstances() {
		ArrayList<Instance> instances = new ArrayList<Instance>();
		final AmazonEC2 ec2 = AmazonEC2ClientBuilder.defaultClient();
		DescribeInstancesRequest request = new DescribeInstancesRequest();
		DescribeInstancesResult response = ec2.describeInstances(request);
		do {	  
			for(Reservation reservation : response.getReservations()) {
				for(Instance instance : reservation.getInstances()) {
					if (instanceContainsBackupTag(instance)) // check that instance has backup tag
						instances.add(instance);
				}
			}
			request.setNextToken(response.getNextToken());
		}
		while(response.getNextToken() != null); 
		return instances;
	}


	private ArrayList<EbsInstanceBlockDevice> listVolumes(ArrayList<Instance> instances) 
	{
		Set<EbsInstanceBlockDevice> hs = new HashSet<>();
		ArrayList<EbsInstanceBlockDevice> volumes= new ArrayList<EbsInstanceBlockDevice>();
		for (Instance instance: instances) {
			List<InstanceBlockDeviceMapping>  mappings=	instance.getBlockDeviceMappings();
			for (InstanceBlockDeviceMapping map:mappings)
				hs.add(map.getEbs());
		}
		volumes.addAll(hs);
		return volumes;
	}

	/**
	 * Creates backups of all volumes that are attached
	 * @param volumes
	 * @param strDate
	 */
	private void createBackUpsVolumes(ArrayList<EbsInstanceBlockDevice> volumes,String strDate)
	{		
		for (EbsInstanceBlockDevice volume:volumes) 
		{
			CreateSnapshotRequest createSnapshotRequest = new CreateSnapshotRequest(volume.getVolumeId(),volume.getVolumeId()+"Lambda-Automated_snapshot");
			List<TagSpecification> ts = new ArrayList<TagSpecification>();
			ts.add(new TagSpecification().withTags(new Tag("deleteday",strDate),new Tag("Name", strDate+"-Lambda_Snapshot-"+volume.getVolumeId()))
					.withResourceType(ResourceType.Snapshot));	
			createSnapshotRequest.setTagSpecifications(ts);
			ec2.createSnapshot(createSnapshotRequest);		
		}
	}

	public void removeOldSnapShots() {
		DescribeSnapshotsRequest snapshotRequest = new DescribeSnapshotsRequest();
		DescribeSnapshotsResult response = ec2.describeSnapshots(snapshotRequest);
		for(Snapshot snapshot : response.getSnapshots()) {
			for (Tag tag:snapshot.getTags()) {
				if (tag.getKey().equals("deleteday") && deletedayPassed(tag.getValue()) ) 
				{
					DeleteSnapshotRequest deleterequest = new DeleteSnapshotRequest();
					deleterequest.setSnapshotId(snapshot.getSnapshotId());
					ec2.deleteSnapshot(deleterequest);
					break;
				}
			}
		}
	}

	protected boolean deletedayPassed(String deleteday) {
		SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy");
		Calendar c= Calendar.getInstance();
		try {
			Date dday = sdf.parse(deleteday);			
			Date currentday= c.getTime();
			boolean preparingToDelete=  dday.getTime()<c.getTimeInMillis();
			if (preparingToDelete)
				System.out.println("Preparing to delete snapshot "+ "current day:"+ sdf.format(currentday) +"deleteday:" +deleteday);	
			return preparingToDelete;
		} catch (ParseException e) {

			return false;
		}
	}

	/**
	 * If EC2 instance has Backup tag with value true, volumes are backed up
	 * @param instance
	 * @return
	 */

	private Boolean instanceContainsBackupTag(Instance instance) {
		for (Tag tag : instance.getTags()) {
			if (tag != null && tag.getKey() != null
					&& tag.getKey().equals("Backup") && tag.getValue().equals("true"))
				return true;
		}		
		return false;
	}
}