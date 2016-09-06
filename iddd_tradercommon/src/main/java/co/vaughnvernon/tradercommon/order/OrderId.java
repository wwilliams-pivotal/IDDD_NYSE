//   Copyright 2012,2013 Vaughn Vernon
//
//   Licensed under the Apache License, Version 2.0 (the "License");
//   you may not use this file except in compliance with the License.
//   You may obtain a copy of the License at
//
//       http://www.apache.org/licenses/LICENSE-2.0
//
//   Unless required by applicable law or agreed to in writing, software
//   distributed under the License is distributed on an "AS IS" BASIS,
//   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//   See the License for the specific language governing permissions and
//   limitations under the License.

package co.vaughnvernon.tradercommon.order;

import java.util.UUID;

public final class OrderId {

	private String id;
	private String colocationId;

	public static OrderId unique() {
		return new OrderId(UUID.randomUUID().toString().toUpperCase());
	}

	public static OrderId unique(String aColocationId) {
		return new OrderId(UUID.randomUUID().toString().toUpperCase(), aColocationId);
	}
	
	/*
	 * Empty constructor for serialization only
	 */
	public OrderId() {
		super();
	}



	public OrderId(String anId) {
		super();

		this.setId(anId);
	}

	public OrderId(String anId, String aColocationId) {
		super();

		this.setId(anId);
		this.setColocationId(aColocationId);
	}

	public String id() {
		return this.id;
	}

	public String colocationId() {
		return colocationId;
	}

	@Override
	public boolean equals(Object anObject) {
		boolean equalObjects = false;
		if (anObject != null && this.getClass() == anObject.getClass()) {
			OrderId typedObject = (OrderId) anObject;
			equalObjects =
					this.id().equals(typedObject.id());
			if (!equalObjects) {
				return false;
			}
			if (this.colocationId() == null && typedObject.colocationId() == null) {
				return true;
			}
			if (this.colocationId() == null || typedObject.colocationId() == null) {
				return false;
			}
			equalObjects =
					this.colocationId().equals(typedObject.colocationId());
		}
		return equalObjects;
	}

	@Override
	public int hashCode() {
		int hashCodeValue =
			+ (95123 * 89)
			+ this.id().hashCode();
		if (this.colocationId() != null) {
			hashCodeValue += this.colocationId().hashCode();
		}

		return hashCodeValue;
	}

	@Override
	public String toString() {
		return "OrderId [id=" + id + ", colocationId=" + colocationId + "]";
	}

	private void setId(String anId) {
		if (anId == null || anId.length() == 0) {
			throw new IllegalArgumentException("Id must be provided.");
		}
		if (anId.length() > 36) {
			throw new IllegalArgumentException("Id must be 36 characters.");
		}
		this.id = anId;
	}
	
	public void setColocationId(String aColocationId) {
		this.colocationId = aColocationId;
	}

}
