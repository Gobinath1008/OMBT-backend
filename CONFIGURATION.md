# Configuration

Set the following environment variables before starting the application:

| Variable | Purpose |
| --- | --- |
| `MONGODB_URI` | MongoDB connection URI for the database containing the `movies` collection; defaults to `mongodb://localhost:27017/MTBS` for local development |
| `MONGODB_DATABASE` | MongoDB database name; defaults to `MTBS` |
| `JWT_SECRET` | Secret used to sign JWTs |
| `CLOUDINARY_CLOUD_NAME` | Cloudinary cloud name |
| `CLOUDINARY_API_KEY` | Cloudinary API key |
| `CLOUDINARY_API_SECRET` | Cloudinary API secret |

`JWT_EXPIRATION_MS` is optional and defaults to `86400000` (24 hours).
`CORS_ALLOWED_ORIGINS` is optional and defaults to `http://localhost:3000`.
For local development, run MongoDB on `localhost:27017` or set `MONGODB_URI`
to a valid MongoDB connection string. To use existing hosted data, set
`MONGODB_URI` to that database's `mongodb://` or `mongodb+srv://` URI and set
`MONGODB_DATABASE` to the database containing the `movies` collection. Set
these variables in the environment used to launch the backend (for example,
the VS Code Java launch configuration or the PowerShell session that starts
Maven). Spring Boot does not load a plain `.env` file automatically. Do not
set `MONGODB_URI` to an empty value or a placeholder.

Do not commit credentials or `.env` files. Rotate any credentials that were
previously stored in source control or shared outside their intended scope.
