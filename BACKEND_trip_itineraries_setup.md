## Restore trip_itineraries table and wiring (Laravel)

This guide recreates the missing `trip_itineraries` table and ensures it works with the mobile app payload.

### 1) Migration

Run:

```bash
php artisan make:migration create_trip_itineraries_table
```

Replace the generated file contents with:

```php
<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration {
    public function up(): void
    {
        Schema::create('trip_itineraries', function (Blueprint $table) {
            $table->id();
            // If your parent table is 'trip_requests'
            $table->foreignId('trip_request_id')
                  ->constrained('trip_requests')
                  ->cascadeOnDelete();

            // Match mobile payload
            $table->decimal('odometer', 10, 2);
            $table->time('time_departure');
            $table->string('departure');
            $table->time('time_arrival')->nullable();
            $table->string('arrival')->nullable();

            $table->timestamps();
            $table->index('trip_request_id');
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('trip_itineraries');
    }
};
```

If your parent table is actually `trips`, change to:

```php
$table->foreignId('trip_id')->constrained('trips')->cascadeOnDelete();
```

And update the model/controller references accordingly.

Apply the migration:

```bash
php artisan migrate
```

### 2) Eloquent models

`app/Models/TripItinerary.php`

```php
<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class TripItinerary extends Model
{
    protected $fillable = [
        'trip_request_id',
        'odometer',
        'time_departure',
        'departure',
        'time_arrival',
        'arrival',
    ];

    public function tripRequest()
    {
        return $this->belongsTo(TripRequest::class, 'trip_request_id');
    }
}
```

In `app/Models/TripRequest.php` add:

```php
public function itineraries()
{
    return $this->hasMany(TripItinerary::class, 'trip_request_id');
}
```

If you use `trips` instead, rename the FK to `trip_id`, update relations accordingly.

### 3) Controller example (Return endpoint persists itinerary)

```php
public function logReturn(Request $request, int $tripId)
{
    $validated = $request->validate([
        'fuel_balance_start' => ['required','numeric'],
        'fuel_purchased'     => ['required','numeric'],
        'fuel_used'          => ['required','numeric'],
        'fuel_balance_end'   => ['required','numeric'],
        'odometer_arrival'   => ['required','numeric'],
        'passenger_details'  => ['required','array'],
        'passenger_details.*.name'        => ['required','string'],
        'passenger_details.*.destination' => ['nullable','string'],
        'passenger_details.*.signature'   => ['nullable','string'],
        'itinerary'          => ['nullable','array'],
        'itinerary.*.odometer'       => ['required','numeric'],
        'itinerary.*.time_departure'  => ['required','date_format:H:i:s'],
        'itinerary.*.departure'       => ['required','string'],
        'itinerary.*.time_arrival'    => ['nullable','date_format:H:i:s'],
        'itinerary.*.arrival'         => ['nullable','string'],
    ]);

    $tripRequest = TripRequest::findOrFail($tripId);

    // ... save other return fields to your models here ...

    if (!empty($validated['itinerary'])) {
        $tripRequest->itineraries()->createMany(
            collect($validated['itinerary'])->map(function ($leg) {
                return [
                    'odometer'       => $leg['odometer'],
                    'time_departure' => $leg['time_departure'],
                    'departure'      => $leg['departure'],
                    'time_arrival'   => $leg['time_arrival'] ?? null,
                    'arrival'        => $leg['arrival'] ?? null,
                ];
            })->all()
        );
    }

    return response()->json(['success' => true]);
}
```

### 4) Raw SQL (quick restore)

```sql
CREATE TABLE `trip_itineraries` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `trip_request_id` bigint unsigned NOT NULL,
  `odometer` decimal(10,2) NOT NULL,
  `time_departure` time NOT NULL,
  `departure` varchar(255) NOT NULL,
  `time_arrival` time DEFAULT NULL,
  `arrival` varchar(255) DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT NULL,
  `updated_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `trip_itineraries_trip_request_id_index` (`trip_request_id`),
  CONSTRAINT `trip_itineraries_trip_request_id_foreign`
    FOREIGN KEY (`trip_request_id`) REFERENCES `trip_requests` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

If your parent table is `trips`, use `trip_id` and reference `trips(id)` instead.

### 5) Mobile payload assumptions

- `itinerary[*].time_departure` and `time_arrival` are 24-hour strings (`HH:mm:ss`).
- `odometer` is a number (double).
- `arrival` can be null for ongoing legs; app may send an empty itinerary if user does not add legs.

### 6) Temporary workaround

Make `itinerary` nullable in the request validation (as above) and skip inserts if empty, so returns work even before the table exists. Once the table is created and migrated, itineraries will persist normally.


