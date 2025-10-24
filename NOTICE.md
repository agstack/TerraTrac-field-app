# NOTICE

## TerraTrac Citizen Science MVP

This project extends TerraTrac with Citizen Science capabilities, integrating with AgStack Foundation services.

## Third-Party Components and Attribution

### AgStack Foundation
- **AgStack User Registry**: User authentication and management
  - Repository: https://github.com/agstack/user-registry
  - License: See original repository
  
- **AgStack Asset Registry**: Geospatial asset registration and geoid generation
  - Repository: https://github.com/agstack/asset-registry
  - License: See original repository
  - Used for: Polygon and point registration, geoid generation (256-byte/16-char alphanumeric unique IDs)

### Matrix SDK
- **Matrix Android SDK 2**: Real-time messaging for Citizen Science community
  - Repository: https://github.com/matrix-org/matrix-android-sdk2
  - License: Apache 2.0
  - Used for: Public room messaging, community feed

### S2 Geometry
- **Google S2 Geometry Library**: S2-L10 cell computation for location tagging
  - Repository: https://github.com/google/s2geometry
  - License: Apache 2.0
  - Used for: Location-based tagging with S2-L10 cells

### TerraTrac Base Application
- **TerraTrac Field App**: Base application for EUDR compliance
  - Repository: https://github.com/agstack/TerraTrac-field-app
  - License: MIT
  - Maintained by: TechnoServe Labs

## Citizen Science Features

### Deep Links
- `https://tt.earthcast.ai/citizenscience` → Citizen Science hub
- `https://tt.earthcast.ai/citizenscience/rain` → Rain reporting form
- `https://tt.earthcast.ai/citizenscience/photo` → Photo sharing form
- `https://tt.earthcast.ai/citizenscience/forecast` → Weather forecast viewer

### WhatsApp Off-Ramp
- Automatic geoid registration for rain gauge locations
- S2-L10 cell encoding for location tagging
- Deep link generation for seamless app integration

### Matrix Integration
- Public Citizen Science room for community sharing
- Automatic hashtag inclusion (#citizenscience)
- S2-L10 location tagging (@s2:10:<token>)

### AgStack Integration
- User authentication via AgStack user-registry
- Asset registration via AgStack asset-registry
- Geoid generation for spatial data management

## Powered by TerraTrac by AgStack

This Citizen Science extension is built on TerraTrac, an open-source agricultural data collection platform developed by TechnoServe Labs and supported by the AgStack Foundation.

## License

This project extends TerraTrac with additional Citizen Science capabilities. The base TerraTrac application is licensed under MIT. Additional components maintain their respective licenses as noted above.

## Contact

For questions about the Citizen Science MVP implementation:
- TerraTrac: https://github.com/agstack/TerraTrac-field-app
- AgStack Foundation: https://agstack.org
- Earthcast: https://earthcast.ai
