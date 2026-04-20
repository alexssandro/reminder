using System;
using Microsoft.EntityFrameworkCore.Migrations;
using Npgsql.EntityFrameworkCore.PostgreSQL.Metadata;

#nullable disable

namespace Reminder.Api.Migrations
{
    /// <inheritdoc />
    public partial class AddWeeklyDaysMask : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.CreateTable(
                name: "reminders",
                columns: table => new
                {
                    Id = table.Column<int>(type: "integer", nullable: false)
                        .Annotation("Npgsql:ValueGenerationStrategy", NpgsqlValueGenerationStrategy.IdentityByDefaultColumn),
                    Description = table.Column<string>(type: "character varying(2000)", maxLength: 2000, nullable: false),
                    ScheduleKind = table.Column<int>(type: "integer", nullable: false),
                    DailyMinuteOfDay = table.Column<int>(type: "integer", nullable: true),
                    OneTimeDueAtUtc = table.Column<DateTime>(type: "timestamp with time zone", nullable: true),
                    WeeklyDaysMask = table.Column<int>(type: "integer", nullable: true),
                    IsActive = table.Column<bool>(type: "boolean", nullable: false),
                    CreatedAtUtc = table.Column<DateTime>(type: "timestamp with time zone", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_reminders", x => x.Id);
                });

            migrationBuilder.CreateTable(
                name: "reminder_occurrences",
                columns: table => new
                {
                    Id = table.Column<int>(type: "integer", nullable: false)
                        .Annotation("Npgsql:ValueGenerationStrategy", NpgsqlValueGenerationStrategy.IdentityByDefaultColumn),
                    ReminderId = table.Column<int>(type: "integer", nullable: false),
                    DueAtUtc = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    FiredAtUtc = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    CheckedAtUtc = table.Column<DateTime>(type: "timestamp with time zone", nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_reminder_occurrences", x => x.Id);
                    table.ForeignKey(
                        name: "FK_reminder_occurrences_reminders_ReminderId",
                        column: x => x.ReminderId,
                        principalTable: "reminders",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateIndex(
                name: "IX_reminder_occurrences_DueAtUtc",
                table: "reminder_occurrences",
                column: "DueAtUtc");

            migrationBuilder.CreateIndex(
                name: "IX_reminder_occurrences_ReminderId_CheckedAtUtc",
                table: "reminder_occurrences",
                columns: new[] { "ReminderId", "CheckedAtUtc" });

            migrationBuilder.CreateIndex(
                name: "IX_reminders_IsActive",
                table: "reminders",
                column: "IsActive");
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropTable(
                name: "reminder_occurrences");

            migrationBuilder.DropTable(
                name: "reminders");
        }
    }
}
